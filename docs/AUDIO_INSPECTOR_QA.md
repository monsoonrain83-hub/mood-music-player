# Read-only audio source inspector

Shows source document display name, duration, file size and bitrate in Converter and Cutter. Reads SAF metadata and Android MediaMetadataRetriever on IO dispatcher. Missing fields remain explicitly unknown. Metadata reads do not modify originals. API 31+ sample rate available in the internal data model.

QA: inaccessible cloud documents, missing metadata, corrupt files, huge file sizes, activity rotation and rapid source changes. No waveform support for compressed formats yet. Do not infer that a format is convertible solely because Android's retriever can inspect it.
