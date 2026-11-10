# ADR-001: Hybrid architecture (media + vectors on phone, model in cloud)

- **Status:** Accepted · 2026-11-10
- **Context:** Search must be accurate, private and cheap. SigLIP 2 So400m is ~4.5 GB and will not run on phones.
- **Decision:** Photos and vectors stay on the phone (ObjectBox). With explicit consent a resized, EXIF-stripped copy goes to AWS Mumbai only to be embedded, then is deleted (24h lifecycle). Face data never leaves the phone.
- **Consequences:** New searches need internet until the on-device encoder ships (Phase 5). Cost scales with indexing volume, so quotas and batching matter. Single-device only until encrypted sync exists.
- **Source:** Blueprint sections 5.3 and 6.2.
