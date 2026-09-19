/**
 * Zero-dependency standard ZIP archive creator using Node.js built-in zlib
 */

import * as zlib from 'zlib';

export interface ZipEntry {
    name: string; // Relative path within archive e.g. "manifest.json", "assets/icon.png"
    data: Buffer;
}

export function createZipArchive(entries: ZipEntry[]): Buffer {
    const localHeaders: Buffer[] = [];
    const centralDirectoryHeaders: Buffer[] = [];
    let offset = 0;

    for (const entry of entries) {
        const nameBuffer = Buffer.from(entry.name.replace(/\\/g, '/'), 'utf8');
        const uncompressedData = entry.data;
        const uncompressedSize = uncompressedData.length;
        const crc = zlib.crc32(uncompressedData);

        // Deflate compression
        const compressedData = zlib.deflateRawSync(uncompressedData);
        const compressedSize = compressedData.length;

        // Fixed DOS time & date (2026-09-19 12:00:00) for reproducible byte-level builds
        const dosTime = (12 << 11) | (0 << 5) | 0;
        const dosDate = ((2026 - 1980) << 9) | (9 << 5) | 19;

        // Local file header (30 bytes + name + compressedData)
        const localHeader = Buffer.alloc(30);
        localHeader.writeUInt32LE(0x04034b50, 0); // Local header signature
        localHeader.writeUInt16LE(20, 4);         // Version needed to extract (2.0)
        localHeader.writeUInt16LE(0, 6);          // General purpose bit flag
        localHeader.writeUInt16LE(8, 8);          // Compression method (8 = DEFLATE)
        localHeader.writeUInt16LE(dosTime, 10);   // File modification time
        localHeader.writeUInt16LE(dosDate, 12);   // File modification date
        localHeader.writeUInt32LE(crc, 14);       // CRC-32
        localHeader.writeUInt32LE(compressedSize, 18);   // Compressed size
        localHeader.writeUInt32LE(uncompressedSize, 22); // Uncompressed size
        localHeader.writeUInt16LE(nameBuffer.length, 26);
        localHeader.writeUInt16LE(0, 28);         // Extra field length

        localHeaders.push(localHeader, nameBuffer, compressedData);

        // Central directory header (46 bytes + name)
        const cdHeader = Buffer.alloc(46);
        cdHeader.writeUInt32LE(0x02014b50, 0);    // Central directory file header signature
        cdHeader.writeUInt16LE(20, 4);            // Version made by
        cdHeader.writeUInt16LE(20, 6);            // Version needed to extract
        cdHeader.writeUInt16LE(0, 8);             // General purpose bit flag
        cdHeader.writeUInt16LE(8, 10);            // Compression method
        cdHeader.writeUInt16LE(dosTime, 12);      // File modification time
        cdHeader.writeUInt16LE(dosDate, 14);      // File modification date
        cdHeader.writeUInt32LE(crc, 16);          // CRC-32
        cdHeader.writeUInt32LE(compressedSize, 20);   // Compressed size
        cdHeader.writeUInt32LE(uncompressedSize, 24); // Uncompressed size
        cdHeader.writeUInt16LE(nameBuffer.length, 28);
        cdHeader.writeUInt16LE(0, 30);            // Extra field length
        cdHeader.writeUInt16LE(0, 32);            // File comment length
        cdHeader.writeUInt16LE(0, 34);            // Disk number where file starts
        cdHeader.writeUInt16LE(0, 36);            // Internal file attributes
        cdHeader.writeUInt32LE(0, 38);            // External file attributes
        cdHeader.writeUInt32LE(offset, 42);       // Relative offset of local file header

        centralDirectoryHeaders.push(cdHeader, nameBuffer);

        offset += localHeader.length + nameBuffer.length + compressedData.length;
    }

    const cdStartOffset = offset;
    const cdBuffer = Buffer.concat(centralDirectoryHeaders);
    const cdSize = cdBuffer.length;

    // End of central directory record (22 bytes)
    const eocd = Buffer.alloc(22);
    eocd.writeUInt32LE(0x06054b50, 0);            // EOCD signature
    eocd.writeUInt16LE(0, 4);                     // Number of this disk
    eocd.writeUInt16LE(0, 6);                     // Disk where central directory starts
    eocd.writeUInt16LE(entries.length, 8);        // Number of central directory records on this disk
    eocd.writeUInt16LE(entries.length, 10);       // Total number of central directory records
    eocd.writeUInt32LE(cdSize, 12);               // Size of central directory
    eocd.writeUInt32LE(cdStartOffset, 16);        // Offset of start of central directory
    eocd.writeUInt16LE(0, 20);                    // ZIP file comment length

    return Buffer.concat([...localHeaders, cdBuffer, eocd]);
}
