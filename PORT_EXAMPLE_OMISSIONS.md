# PORT_EXAMPLE_OMISSIONS.md

Examples in the Python reference SDK that are deliberately omitted from this
Java port. Each entry is the Python relative path plus a one-line rationale.
The list is consumed by `audit_example_parity.py`; un-recorded omissions fail
the audit.

## Search-related (Python-only)

The Python `local_search_agent.py` example demonstrates an in-process vector
index built from local files via `signalwire-search` -- a Python-only optional
extra (FAISS / sentence-transformers / numpy). The Java SDK does not ship a
search subsystem (parity-with-Python excludes search per `PORTING_GUIDE.md`).

- `local_search_agent` -- depends on Python's `signalwire.search` package
  (sqlite/pgvector index builder, embeddings via sentence-transformers /
  FAISS). The Java port omits the entire `signalwire/search/` surface per
  `PORTING_GUIDE.md` § "What to Skip" -- see also `PORT_OMISSIONS.md` for
  the symbol-level surface omissions in this area. The network-mode
  (`remote_url`) path of the `native_vector_search` skill is fully
  implemented and exercised by `audit_skills_dispatch`.
