import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Attachment } from './models';

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
   *  Lambda (the Function URL's synchronous payload limit). */
  upload(collectionId: string, file: File): Observable<Attachment> {
    return new Observable<Attachment>((subscriber) => {
      // Read the whole file into memory FIRST and upload those bytes, not the File handle.
      // On iPhone (WebKit) a photo picked from the library is a temporary file that can be
      // gone by the time the PUT streams it - the request still "succeeds", but S3 stores a
      // 0-byte object that later shows as a broken image (seen on production). The backend's
      // confirm step also rejects a size mismatch, as a second line of defence.
      file.arrayBuffer().then(
        (buffer) => {
          if (buffer.byteLength === 0 || buffer.byteLength !== file.size) {
            subscriber.error(new Error(`Could not read ${file.name}: got ${buffer.byteLength} of ${file.size} bytes`));
            return;
          }
          this.uploadBytes(collectionId, file, new Blob([buffer], { type: file.type })).subscribe(subscriber);
        },
        (err) => subscriber.error(err),
      );
    });
  }

  private uploadBytes(collectionId: string, file: File, bytes: Blob): Observable<Attachment> {
    return new Observable<Attachment>((subscriber) => {
      this.requestUpload(collectionId, file.name, file.type, bytes.size).subscribe({
        next: (ticket) => {
          this.putToS3(ticket.uploadUrl, bytes).subscribe({
            next: () => {
              this.confirmUpload(collectionId, ticket.attachmentId, file.name, file.type, bytes.size).subscribe({
                next: (attachment) => {
                  subscriber.next(attachment);
                  subscriber.complete();
                },
                error: (err) => subscriber.error(err),
              });
            },
            error: (err) => subscriber.error(err),
          });
        },
        error: (err) => subscriber.error(err),
      });
    });
  }

  delete(collectionId: string, attachmentId: string): Observable<object> {
    return this.http.delete(`/api/collections/${collectionId}/attachments/${attachmentId}`);
  }
}
