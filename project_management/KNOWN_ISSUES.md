# Known Issues & Risk Management: Yemen Water Survey Field Application

**Project Name:** Yemen Water Survey Field Application  
**Last Updated:** 2026-08-13  

---

## Active Risk & Technical Issue Tracking

| Risk / Issue ID | Description | Severity | Impact Area | Mitigation Strategy / Planned Resolution |
| :--- | :--- | :---: | :--- | :--- |
| **KI-001** | Large uncompressed images attached to survey records rapidly consume phone device storage. | High | Media / Storage | Implement automated client-side JPEG image compression (<300KB per photo) before persisting file path to attachments table (Phase 5). |
| **KI-002** | High-density GPS accuracy degradation in mountainous or deep wadi terrain across Yemen. | Medium | Location / GPS | Display real-time horizontal accuracy radius (meters) in Arabic; require accuracy threshold (<15m) or explicit user override note before form submission (Phase 5). |
| **KI-003** | Arabic font rendering alignment on rendered PDF templates across different device engines. | Medium | PDF Generator | Bundle embedded UTF-8 Arabic fonts (`Amiri-Regular.ttf`, `Amiri-Bold.ttf`) directly in PDF generation engine to prevent font fallback issues (Phase 7). |
| **KI-004** | Field enumerators entering missing village names as free text leading to data duplication. | Low | Admin Reference | Enforce cascading administrative selection controls and route missing village entries through the Administrative Change Request queue (Phase 8). |
