# Photos API

`/api/image`, from [`ImageRoute.kt`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/routing/ImageRoute.kt).
Uploading photos and fetching them back. A photo is uploaded on its own first, then kept by saving the note or
inventory item it's on, which links it. Anything never linked is cleared up a day later. The whole lifecycle is in
[Photos](../server/photos.md).

### `POST /api/image/upload`

**Auth:** normal

Uploads one photo. The server turns it upright from its EXIF, strips its metadata, and writes the three sizes. It
starts `PENDING` until a note or item links it.

- **Body:** `multipart/form-data` with the file in a part named `image` and, optionally, a `description` text
  part. At most 25 MB and 50 megapixels, in any format Java's `ImageIO` can read (JPEG, PNG, GIF, BMP).
- **`201`** `StoredImage`, with the id to put on the note or item.
- **`400`** no `image` part, or the upload was cut off. **`413`** too big, in bytes or pixels. **`415`** not an
  image it can read. **`500`** it couldn't be processed or saved.

A body that says up front (`Content-Length`) it's over the limit is refused before anything is read.

### `GET /api/image/{identifier}`

**Auth:** restricted

The photo's details, not the picture.

- **`200`** `StoredImage { identifier, description, tags, width, height, sizeBytes }`. **`404`** no such photo,
  it's been deleted, or it's pending and someone else uploaded it.

### `GET /api/image/{identifier}/{variant}`

**Auth:** restricted

The picture itself, as a JPEG.

- **Path:** `variant` is `thumb` (320 px on the long edge, for lists), `display` (1600 px, for the pager) or
  `original` (up to 2560 px). Smaller photos are never enlarged.
- **`200`** the file, with `Cache-Control: private, max-age=31536000, immutable` and an `ETag` made from the photo's
  hash. Photos never change once uploaded.
- **`304`** when `If-None-Match` matches the `ETag`. **`404`** no such photo or variant, it's been deleted, or it's
  pending and someone else uploaded it.

Images load in the client through Coil on the logged in HTTP client, so they carry the bearer token; a plain
`<img src>` in a browser won't work. See [Transfers and photos](../client/transfers-and-photos.md).

## Linking

There's no route to link or delete a photo by itself:

- note photos are the `images` on the note, saved with [the note routes](notes.md),
- an item's own photos are set as a whole list with
  [`POST /api/data/inventory/set/images/{identifier}`](inventory.md#post-apidatainventorysetimagesidentifier).

Either way, photos that were on it and aren't in the new list are marked deleted, and a photo that's already on
something else can't be taken.

## See also

- [Photos](../server/photos.md)
- [Transfers and photos](../client/transfers-and-photos.md)
