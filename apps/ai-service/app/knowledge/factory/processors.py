from typing import Dict, Any, Type
from app.knowledge.metadata.extractors import (
    MetadataExtractor, ResumeMetadataExtractor, JobMetadataExtractor, 
    CompanyMetadataExtractor, DefaultMetadataExtractor
)
from app.knowledge.chunking.strategies import (
    ChunkStrategy, ResumeChunkStrategy, JobChunkStrategy, 
    CompanyChunkStrategy, ConversationChunkStrategy, DefaultChunkStrategy
)
from app.knowledge.retrieval.retrievers import (
    Retriever, ResumeRetriever, JobRetriever, 
    CompanyRetriever, ConversationRetriever, DefaultRetriever
)

class DocumentProcessor:
    def __init__(
        self,
        extractor: MetadataExtractor,
        chunker: ChunkStrategy,
        retriever: Retriever
    ):
        self.extractor = extractor
        self.chunker = chunker
        self.retriever = retriever

class DocumentFactory:
    def __init__(self):
        self._processors: Dict[str, DocumentProcessor] = {}

    def register(self, doc_type: str, processor: DocumentProcessor):
        self._processors[doc_type.upper()] = processor

    def get_processor(self, doc_type: str) -> DocumentProcessor:
        doc_type_upper = doc_type.upper()
        if doc_type_upper not in self._processors:
            # Fall back to default
            return DocumentProcessor(
                DefaultMetadataExtractor(),
                DefaultChunkStrategy(),
                DefaultRetriever()
            )
        return self._processors[doc_type_upper]

# Global Factory Setup
document_factory = DocumentFactory()
document_factory.register("RESUME", DocumentProcessor(
    ResumeMetadataExtractor(),
    ResumeChunkStrategy(),
    ResumeRetriever()
))
document_factory.register("JOB", DocumentProcessor(
    JobMetadataExtractor(),
    JobChunkStrategy(),
    JobRetriever()
))
document_factory.register("COMPANY", DocumentProcessor(
    CompanyMetadataExtractor(),
    CompanyChunkStrategy(),
    CompanyRetriever()
))
document_factory.register("INTERVIEW", DocumentProcessor(
    DefaultMetadataExtractor(),
    ConversationChunkStrategy(),
    ConversationRetriever()
))
