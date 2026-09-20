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

  private putToS3(uploadUrl: string, file: File): Observable<object> {
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
      this.requestUpload(collectionId, file.name, file.type, file.size).subscribe({
        next: (ticket) => {
          this.putToS3(ticket.uploadUrl, file).subscribe({
            next: () => {
              this.confirmUpload(collectionId, ticket.attachmentId, file.name, file.type, file.size).subscribe({
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
