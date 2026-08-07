# CareerPilot OS - Milestone 8: AI Matching Engine Documentation

## Overview

Milestone 8 implements a comprehensive AI Matching Engine that compares three structured knowledge models (Candidate, Company, and Job) to produce explainable, weighted recommendations with gap analysis and ranking capabilities.

## Architecture

### Modular Pipeline

```
Candidate Knowledge → Company Knowledge → Job Knowledge
↓
Feature Normalization
↓
Weighted Scoring
↓
Gap Analysis
↓
Recommendation Generation
↓
Ranking
↓
Match Explanation
↓
Recommendation Ready
```

### Core Components

#### 1. Profile Analyzers

**CandidateProfileAnalyzer**
- Analyzes: Skills, Technologies, Experience, Education, Certifications, Projects, Languages, ATS Quality, Career Progression
- Output: Normalized `CandidateProfile`

**CompanyProfileAnalyzer**
- Analyzes: Technology Stack, Industry, Engineering Culture, Hiring Signals, Company Size, Remote Policy, Growth Indicators, Benefits, Learning Opportunities
- Output: Normalized `CompanyProfile`

**JobProfileAnalyzer**
- Analyzes: Required Skills, Preferred Skills, Responsibilities, Seniority, Salary, Location, Employment Type, Required Certifications, Education, Technology Stack
- Output: Normalized `JobProfile`

#### 2. Scoring Modules

Independent scoring modules implementing the `MatchScorer` interface:

- **SkillMatchScorer**: Compares candidate skills with job requirements
- **ExperienceMatchScorer**: Evaluates experience level against job seniority
- **TechnologyMatchScorer**: Matches technology stack with job/company requirements
- **EducationMatchScorer**: Assesses education level against job requirements
- **CertificationMatchScorer**: Verifies required certifications
- **ProjectMatchScorer**: Evaluates project relevance and technologies
- **ResponsibilityMatchScorer**: Matches candidate summary with job responsibilities
- **IndustryMatchScorer**: Aligns candidate experience with company industry
- **LocationMatchScorer**: Evaluates location compatibility
- **RemotePreferenceMatchScorer**: Matches remote work preferences
- **EmploymentTypeMatchScorer**: Validates employment type compatibility
- **SalaryMatchScorer**: Compares salary expectations with job offer
- **CareerGrowthMatchScorer**: Assesses career growth opportunities
- **EngineeringCultureMatchScorer**: Evaluates cultural fit
- **LearningOpportunityMatchScorer**: Measures learning and development opportunities

#### 3. Weight Configuration

**MatchingWeightsConfig**
- Default weights (configurable):
  - Skill Match: 30%
  - Experience: 20%
  - Projects: 10%
  - Technology: 15%
  - Location: 5%
  - Salary: 5%
  - Culture: 5%
  - Education: 5%
  - Certifications: 5%
  - Other factors: 5% each
- Supports custom weight overrides
- Validates weight sums to approximately 1.0

#### 4. Score Aggregator

**ScoreAggregator**
- Combines individual scorer results using configured weights
- Produces overall match score and individual factor scores
- Handles missing/null scores gracefully

#### 5. Gap Analysis Engine

**GapAnalysisEngine**
- Identifies gaps across multiple dimensions:
  - **Critical Gaps**: Required skills, experience, certifications
  - **Recommended Improvements**: Preferred skills, technologies, projects
  - **Optional Improvements**: Education enhancements, project details
- Categorizes gaps by severity and type
- Provides actionable improvement suggestions

#### 6. Recommendation Engine

**RecommendationEngine**
- Generates deterministic, rule-based recommendations
- Categories:
  - Skill Acquisition/Enhancement
  - Technology Experience
  - Experience Gain/Enhancement
  - Certification
  - Education
  - Project Build/Enhancement
  - Resume Improvement/Optimization
- Provides estimated effort for each recommendation

#### 7. Ranking Engine

**RankingEngine**
- Multiple ranking strategies:
  - **OVERALL_SCORE**: Default by overall match score
  - **SKILL_SCORE**: By skill match score
  - **COMPANY_FIT**: By cultural fit and growth opportunities
  - **CAREER_GROWTH**: By career growth potential
  - **SALARY**: By salary alignment
  - **LEARNING_OPPORTUNITY**: By learning opportunities
  - **CUSTOM**: By user-defined custom weights
- Supports filtering by minimum score threshold
- Supports result limiting

#### 8. Explanation Generator

**ExplanationGenerator**
- Produces human-readable match explanations
- Includes:
  - Overall match summary
  - Detailed score breakdown
  - Strengths identification
  - Weaknesses identification
  - Gap analysis summary
- Provides confidence scores

#### 9. Matching Engine (Orchestrator)

**MatchingEngine**
- Central orchestration service
- Coordinates all components
- Manages caching
- Publishes events
- Provides bulk matching capabilities

#### 10. Caching Layer

**MatchingCache**
- In-memory caching of match results
- Reduces redundant computations
- Cache key based on candidate + job IDs
- Automatic cache invalidation on weight changes

## API Endpoints

### POST /api/v1/ai/matching/match
Matches a single candidate to a single job.

**Request Body:**
```json
{
  "candidateId": "uuid",
  "jobId": "uuid",
  "companyId": "uuid",
  "userId": "uuid",
  "candidateKnowledge": {},
  "candidateQualityMetrics": {},
  "candidatePreferences": {},
  "companyKnowledge": {},
  "companyMetadata": {},
  "companyInsights": {},
  "jobKnowledge": {},
  "jobMetadata": {},
  "jobInsights": {}
}
```

**Response:**
```json
{
  "matchId": "uuid",
  "candidateId": "uuid",
  "jobId": "uuid",
  "companyId": "uuid",
  "userId": "uuid",
  "overallScore": 87.5,
  "individualScores": {
    "skillMatch": 92.0,
    "experienceMatch": 80.0,
    "technologyMatch": 85.0
  },
  "strengths": ["Strong skill alignment"],
  "weaknesses": ["Limited experience"],
  "criticalGaps": [],
  "recommendedImprovements": [],
  "optionalImprovements": [],
  "recommendations": [],
  "confidenceScore": 0.85,
  "explanation": "Detailed explanation...",
  "matchedSkills": ["java", "spring"],
  "missingSkills": []
}
```

### POST /api/v1/ai/matching/match/bulk
Matches a single candidate to multiple jobs efficiently.

### POST /api/v1/ai/matching/gap-analysis
Performs detailed gap analysis without full matching.

### POST /api/v1/ai/matching/recommendations
Generates improvement recommendations based on gap analysis.

### POST /api/v1/ai/matching/rank-jobs
Ranks jobs based on specified strategy.

### GET /api/v1/ai/matching/weights
Retrieves current weight configuration.

### POST /api/v1/ai/matching/weights
Updates weight configuration.

### POST /api/v1/ai/matching/weights/reset
Resets weights to default values.

## Event Integration

### Consumed Events
- **ResumeAnalysisCompletedEvent**: Triggers when resume analysis is complete
- **CompanyAnalysisCompletedEvent**: Triggers when company analysis is complete
- **JobAnalysisCompletedEvent**: Triggers when job analysis is complete

### Published Events
- **MatchCompletedEvent**: Published when matching is complete
- **GapAnalysisCompletedEvent**: Published when gap analysis is complete
- **RecommendationsGeneratedEvent**: Published when recommendations are generated
- **RankingCompletedEvent**: Published when ranking is complete

## Performance Optimizations

1. **Match Result Caching**: Avoids redundant calculations for repeated matches
2. **Incremental Recalculation**: Only recalculates when inputs change
3. **Batch Matching**: Efficiently processes multiple job matches
4. **Parallel Scoring**: Independent scorers can run in parallel

## Testing

### Test Coverage

**Unit Tests:**
- `SkillMatchScorerTest`: Tests skill matching logic
- `ExperienceMatchScorerTest`: Tests experience evaluation
- `TechnologyMatchScorerTest`: Tests technology stack matching
- `EngineeringCultureMatchScorerTest`: Tests cultural fit assessment
- `MatchingWeightsConfigTest`: Tests weight configuration
- `GapAnalysisEngineTest`: Tests gap identification
- `RankingEngineTest`: Tests ranking strategies

**Test Scenarios:**
- Strong matches (90%+)
- Weak matches (<50%)
- Missing skills
- Overqualified candidates
- Fresh graduates
- Career switchers
- Remote preference conflicts
- Salary mismatches

### Target Coverage
- Backend: ≥90%
- AI Platform: ≥90%

## Data Models

### MatchResultDto
```java
public class MatchResultDto {
    private UUID matchId;
    private UUID candidateId;
    private UUID jobId;
    private UUID companyId;
    private UUID userId;
    private double overallScore;
    private Map<String, Double> individualScores;
    private List<String> strengths;
    private List<String> weaknesses;
    private List<GapItemDto> criticalGaps;
    private List<GapItemDto> recommendedImprovements;
    private List<GapItemDto> optionalImprovements;
    private List<RecommendationItemDto> recommendations;
    private double confidenceScore;
    private String explanation;
    private List<String> matchedSkills;
    private List<String> missingSkills;
}
```

### GapItemDto
```java
public class GapItemDto {
    private String type;           // SKILL, EXPERIENCE, CERTIFICATION, etc.
    private String name;           // Specific gap name
    private String severity;       // CRITICAL, RECOMMENDED, OPTIONAL
    private String description;    // Human-readable description
}
```

### RecommendationItemDto
```java
public class RecommendationItemDto {
    private String type;              // SKILL_ACQUISITION, PROJECT_BUILD, etc.
    private String priority;          // HIGH, MEDIUM, LOW
    private String action;            // Specific action to take
    private String description;       // Detailed description
    private String estimatedEffort;   // Time estimate (e.g., "2-4 weeks")
}
```

## Key Design Decisions

1. **Rule-Based Recommendations**: Uses deterministic rules instead of LLMs for consistency and performance
2. **Independent Scorers**: Each scoring module is independent for modularity and testability
3. **Configurable Weights**: Allows customization without code changes
4. **Explainable Results**: Every match includes detailed explanations
5. **Caching Strategy**: In-memory caching for performance
6. **Event-Driven**: Integrates with existing event architecture
7. **Gap Separation**: Distinguishes between critical, recommended, and optional improvements

## Future Enhancements

1. **Persistent Match Storage**: Store match results in database for historical analysis
2. **Machine Learning Models**: Enhance scoring with ML-based patterns
3. **Real-time Matching**: WebSocket-based real-time match updates
4. **Advanced Caching**: Redis-based distributed caching
5. **A/B Testing**: Test different weight configurations
6. **User Feedback**: Incorporate user feedback into scoring
7. **Semantic Matching**: Use embeddings for semantic skill matching

## Definition of Done

Milestone 8 is complete when:
- ✅ Candidate, Company, and Job knowledge are matched successfully
- ✅ Individual scoring modules work independently
- ✅ Weighted scoring is configurable
- ✅ Gap analysis is generated
- ✅ Recommendations are generated deterministically
- ✅ Ranking works correctly
- ✅ Match explanations are available
- ✅ Batch matching is supported
- ✅ Caching avoids unnecessary recalculation
- ✅ Integration tests pass
- ✅ Documentation is updated

## Notes

- **Not Implemented**: Automatic job applications, resume rewriting, cover letter generation, interview preparation, email automation (reserved for later milestones)
- **Focus**: Exclusively on producing accurate, explainable, and configurable AI matching
- **Performance**: Current implementation uses in-memory caching; future versions may use distributed caching
- **Scalability**: Architecture supports horizontal scaling through stateless design
