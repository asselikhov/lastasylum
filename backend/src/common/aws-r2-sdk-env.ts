/**
 * Cloudflare R2 + AWS SDK for JS v3: newer middleware may add default request checksums
 * that R2 rejects (500 on PutObject). These env defaults must be set before any
 * `@aws-sdk/client-s3` module is first evaluated.
 *
 * @see https://community.cloudflare.com/t/aws-sdk-client-s3-v3-729-0-breaks-uploadpart-and-putobject-r2-s3-api-compatibility/758637
 */
process.env.AWS_REQUEST_CHECKSUM_CALCULATION ??= 'WHEN_REQUIRED';
process.env.AWS_RESPONSE_CHECKSUM_VALIDATION ??= 'WHEN_REQUIRED';
