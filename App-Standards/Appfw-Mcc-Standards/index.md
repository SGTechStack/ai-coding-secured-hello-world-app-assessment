> **All directories must be considered.** For each business feature, do not skip any listed directory — each one contains standards or context required for a correct implementation.

> **Build order matters.** For each business feature, all directories listed must be visited in order — each layer depends on the one above it, so build them top-down.

### MCNS Core (Single Notification)
MCNS notification, send SMS, send email, notification service integration, MCNS rate limiting, MCNS retry
**Build order (read in sequence):**
1. [Appfw-Shared-Auth-Standards](Appfw-Shared-Auth-Standards/)
2. [Appfw-Mcns-Standards/MCNS_Core](Appfw-Mcns-Standards/MCNS_Core/)

### MCNS Batch (Batch Retry Pipeline)
MCNS batch retry, batch notification pipeline, failed notification reprocessing
**Build order (read in sequence):**
1. [Appfw-Shared-Auth-Standards](Appfw-Shared-Auth-Standards/)
2. [Appfw-Mcns-Standards/MCNS_Core](Appfw-Mcns-Standards/MCNS_Core/)
3. [Appfw-Mcns-Standards/MCNS_Batch](Appfw-Mcns-Standards/MCNS_Batch/)

### MPDS Retrieval
MPDS retrieval, personnel data lookup, MPDS query, MPDS response model, MPDS frontend guide
**Build order (read in sequence):**
1. [Appfw-Shared-Auth-Standards](Appfw-Shared-Auth-Standards/)
2. [Appfw-Mpds-Standards](Appfw-Mpds-Standards/)
