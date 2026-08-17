# Yemen Water Survey Field Application
**Complete Native Android Offline-First Field Data Collection & Supervisor Synchronization Platform**

## System Overview
- **Platform**: Native Android (Kotlin, Jetpack Compose, Material 3, Room v5)
- **Architecture**: Clean Architecture (Domain / Data / Presentation / Core)
- **Target Geography**: Republic of Yemen (OCHA/IMMAP P-codes Admin1/2/3 + Yemen-Info Auxiliary Village Index)
- **Survey Archetypes**: Water Wells (`آبار المياه`), Springs (`العيون والينابيع`), Dams & Barriers (`السدود والحواجز`)
- **Key Modules**:
  - Offline Point-in-Polygon TopoJSON GIS Engine
  - Dynamic Form & Schema Validation Engine (.zip Form Packages)
  - Bi-directional Offline Synchronization Pipeline (`.ywsync` format with SHA-256 validation)
  - Controlled Supervisor Merge & Audit Log Engine
  - Native Multi-Sheet OOXML Excel & Arabic PDF Export Engines
