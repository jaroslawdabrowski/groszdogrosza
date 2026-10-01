import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, from, switchMap, throwError } from 'rxjs';
import { Attachment } from './models';

/** Which step of an attachment upload failed - see AttachmentApiService.upload. */
export class UploadError extends Error {
  constructor(
    readonly step: 'read' | 'request' | 'put' | 'confirm',
    detail: string,
  ) {
    super(`${step}: ${detail}`);
  }
}

function describe(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    const body = typeof err.error === 'string' ? err.error : JSON.stringify(err.error);
    return `HTTP ${err.status} ${err.statusText} ${(body ?? '').slice(0, 300)}`;
  }
  if (err instanceof Error) {
    return `${err.name}: ${err.message}`;
  }
  return String(err);
}

/** Allowed file types/max size - the user's own explicit choice (asked via
 *  AskUserQuestion): images a phone camera produces plus PDF, capped at 10MB. Mirrors the
 *  backend's AttachmentPolicy - this is a UX convenience (fail fast before even starting an
 *  upload), the backend is still the actual enforcement point. */
export const ALLOWED_ATTACHMENT_TYPES = ['image/jpeg', 'image/png', 'image/heic', 'application/pdf'];
export const MAX_ATTACHMENT_SIZE_BYTES = 10 * 1024 * 1024;

@Injectable({ providedIn: 'root' })
export class AttachmentApiService {
  private readonly http = inject(HttpClient);

  list(collectionId: string): Observable<Attachment[]> {
    return this.http.get<Attachment[]>(`/api/collections/${collectionId}/attachments`);
  }

  private requestUpload(
    collectionId: string,
    fileName: string,
    contentType: string,
    sizeBytes: number,
  ): Observable<{ attachmentId: string; uploadUrl: string }> {
    return this.http.post<{ attachmentId: string; uploadUrl: string }>(
      `/api/collections/${collectionId}/attachments/upload-url`,
      { fileName, contentType, sizeBytes },
    );
  }

  private putToS3(uploadUrl: string, file: Blob): Observable<object> {
    // Not through the app's own HttpClient base/interceptor chain conceptually, but the
    // same HttpClient instance works fine for an absolute cross-origin URL - the
    // authInterceptor only attaches a bearer token to same-origin /api/* requests (see
    // core/auth.interceptor.ts), so it's a no-op here, which is correct: S3 doesn't want a
    // Cognito bearer token, the presigned URL itself is the credential.
    return this.http.put(uploadUrl, file, { headers: { 'Content-Type': file.type } });
  }

  private confirmUpload(
    collectionId: string,
    attachmentId: string,
    fileName: string,
    contentType: string,
    sizeBytes: number,
  ): Observable<Attachment> {
    return this.http.post<Attachment>(`/api/collections/${collectionId}/attachments/${attachmentId}/confirm`, {
      fileName,
      contentType,
      sizeBytes,
    });
  }

  /** The full three-step direct-to-S3 upload flow in one call - see backend
   *  RequestAttachmentUploadUseCase/ConfirmAttachmentUploadUseCase's javadoc for why it's
   *  three separate HTTP calls under the hood rather than a single multipart POST to the
   *  Lambda (the Function URL's synchronous payload limit).
   *
   *  The file is read into memory first and those bytes are uploaded, not the File handle:
   *  on an iPhone a photo picked from the library once reached S3 as 0 bytes. Every step
   *  tags its failure (UploadError.step), so a failed upload can be reported and diagnosed
   *  - see reportFailure. */
  upload(collectionId: string, file: File): Observable<Attachment> {
    return from(file.arrayBuffer()).pipe(
      catchError((err) => throwError(() => new UploadError('read', describe(err)))),
      switchMap((buffer) => {
        // Only an EMPTY read is refused. The size iOS reports for a picked photo can differ
        // from the bytes it hands over (it converts HEIC to JPEG on the way), so the bytes
        // actually read are what gets declared to the backend, not file.size.
        if (buffer.byteLength === 0) {
          return throwError(() => new UploadError('read', `0 bytes read, file.size=${file.size}`));
        }
        const bytes = new Blob([buffer], { type: file.type });
        return this.requestUpload(collectionId, file.name, file.type, bytes.size).pipe(
          catchError((err) => throwError(() => new UploadError('request', describe(err)))),
          switchMap((ticket) =>
            this.putToS3(ticket.uploadUrl, bytes).pipe(
              catchError((err) => throwError(() => new UploadError('put', describe(err)))),
              switchMap(() =>
                this.confirmUpload(collectionId, ticket.attachmentId, file.name, file.type, bytes.size).pipe(
                  catchError((err) => throwError(() => new UploadError('confirm', describe(err)))),
                ),
              ),
            ),
          ),
        );
      }),
    );
  }

  /** Writes a failed upload into the server log (POST /api/client-log), since the failing
   *  steps run in the browser where nobody can see them. Best effort - its own failure is
   *  ignored. */
  reportFailure(file: File, err: unknown): void {
    const step = err instanceof UploadError ? err.step : 'unknown';
    const message = `${describe(err)} | file=${file.name} type=${file.type || '-'} size=${file.size}`;
    this.http.post('/api/client-log', { context: `attachment-upload:${step}`, message }).subscribe({ error: () => {} });
  }

  delete(collectionId: string, attachmentId: string): Observable<object> {
    return this.http.delete(`/api/collections/${collectionId}/attachments/${attachmentId}`);
  }
}
