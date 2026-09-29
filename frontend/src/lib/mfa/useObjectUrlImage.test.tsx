import { render, screen } from '@testing-library/react'
import { StrictMode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useObjectUrlImage } from './useObjectUrlImage'

/** Renders the image only while there is a blob, as the enrolment page does. */
function QrImage({ blob }: { blob: Blob | undefined }) {
  const image = useObjectUrlImage(blob)
  return blob ? <img ref={image} alt="QR code" /> : <p>No image</p>
}

/** Stubs the object-URL API, which jsdom lacks, and records every create, revoke and revoke-while-shown. */
function stubObjectUrls() {
  const created: string[] = []
  const revoked: string[] = []
  const revokedWhileShown: string[] = []
  vi.spyOn(URL, 'createObjectURL').mockImplementation(() => {
    const url = `blob:http://localhost/${created.length + 1}`
    created.push(url)
    return url
  })
  vi.spyOn(URL, 'revokeObjectURL').mockImplementation((url: string) => {
    if (document.querySelector(`img[src="${url}"]`)) {
      revokedWhileShown.push(url)
    }
    revoked.push(url)
  })
  return { created, revoked, revokedWhileShown }
}

const png = (byte: number) => new Blob([new Uint8Array([0x89, byte])], { type: 'image/png' })

function inStrictMode(blob: Blob | undefined) {
  return (
    <StrictMode>
      <QrImage blob={blob} />
    </StrictMode>
  )
}

describe('T-FE-003: the QR object-URL effect is symmetric', () => {
  let urls: ReturnType<typeof stubObjectUrls>

  /** The image's current URL, which must never be one already revoked. */
  const shownUrl = () => {
    const src = screen.getByRole('img', { name: 'QR code' }).getAttribute('src')
    expect(urls.revoked).not.toContain(src)
    return src
  }

  beforeEach(() => {
    urls = stubObjectUrls()
  })

  afterEach(() => vi.restoreAllMocks())

  it('T-FE-003: revokes once per create across a StrictMode remount, a replacement, the error path, a re-show and unmount', () => {
    const { rerender, unmount } = render(inStrictMode(png(1)))
    // StrictMode's mount, unmount and remount: the first URL is revoked, the second is shown.
    expect(urls.created).toHaveLength(2)
    expect(urls.revoked).toEqual([urls.created[0]])
    expect(shownUrl()).toBe(urls.created[1])

    // Replacement: a new QR code revokes the old URL and shows a fresh one.
    const second = png(2)
    rerender(inStrictMode(second))
    expect(shownUrl()).toBe(urls.created[2])
    expect(urls.revoked).toEqual([urls.created[0], urls.created[1]])

    // The error path and the verified flip both clear the blob: the URL is revoked and nothing is shown.
    rerender(inStrictMode(undefined))
    expect(screen.getByText('No image')).toBeInTheDocument()
    expect(urls.revoked).toEqual(urls.created)

    // The same blob shown again gets a fresh URL, never its revoked one; then unmount revokes that too.
    rerender(inStrictMode(second))
    expect(shownUrl()).toBe(urls.created[3])
    unmount()

    expect(urls.created).toHaveLength(4)
    expect(urls.revoked).toEqual(urls.created)
    expect(urls.revokedWhileShown).toEqual([])
  })

  it('T-FE-003: takes the URL off a still-mounted image before revoking it', () => {
    function AlwaysShown({ blob }: { blob: Blob | undefined }) {
      return <img ref={useObjectUrlImage(blob)} alt="QR code" />
    }
    const { rerender } = render(<AlwaysShown blob={png(1)} />)
    expect(shownUrl()).toBe(urls.created[0])

    rerender(<AlwaysShown blob={undefined} />)

    expect(screen.getByRole('img', { name: 'QR code' })).not.toHaveAttribute('src')
    expect(urls.revoked).toEqual(urls.created)
    expect(urls.revokedWhileShown).toEqual([])
  })

  it('never creates a URL when there is no blob', () => {
    render(<QrImage blob={undefined} />)

    expect(screen.getByText('No image')).toBeInTheDocument()
    expect(urls.created).toEqual([])
  })
})
