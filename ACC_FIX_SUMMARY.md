# ACC Query Fix - Missing PART_SECTION_CODE Filter

## Issue Summary
The `fetchACCDataForMultipleIndicatorChange` method was returning 0 ACC rules for certain indicators because it was missing the `PART_SECTION_CODE` filter in the SQL WHERE clause.

## Root Cause Analysis

### Symptoms
- Queries with indicators `[2, 4]` (SUPPLIER_CHANGE + another indicator, excluding PART_COLOR_CODE_CHANGE) returned 0 results
- The same part number with different MTC types (A00, A06, A21, A26) found ACC rules successfully using the `fetchACCData` method
- MTC types C01, A01, C06 consistently returned 0 results

### Investigation Findings

1. **Successful Queries** (`fetchACCData` with `SUPP_CHANGE_MATCH`):
   - Always included: `AND ACC.PART_SECTION_CODE= 'F28' WITH UR`
   - Found 1 ACC rule for MTC types: A00, A06, A26, A21
   - Part Color Codes: TYPE28, TYPE83

2. **Failing Queries** (`fetchACCDataForMultipleIndicatorChange` with indicators `[2, 4]`):
   - **Missing**: `PART_SECTION_CODE` filter
   - Returned 0 ACC rules
   - MTC types: C01, A01, C06
   - Part Color Code: TYPE13

### Root Cause

In `ACCProcessingBatchDAO.java`, the `fetchACCDataForMultipleIndicatorChange` method has logic to handle different indicator combinations:

```java
// Line ~1000-1009: Handles PROC_GROUP_CHANGE
if(indicator.contains(PROC_GROUP_CHANGE)) {
    // Adds PART_SECTION_CODE ✓
}

// Line ~1009-1016: Handles DESIGN_SECTION_CHANGE  
else if(indicator.contains(DESIGN_SECTION_CHANGE)) {
    // Adds PART_SECTION_CODE ✓
}

// Line ~1018-1028: Handles cases where PART_COLOR_CODE_CHANGE is NOT present
else if(!indicator.contains(PART_COLOR_CODE_CHANGE)) {
    // Conditionally adds PART_COLOR_CODE
    // ❌ MISSING: Does NOT add PART_SECTION_CODE
}

// Line ~1029-1037: Final else for other cases
else {
    // Adds both PROC_SECT_CODE and PART_SECTION_CODE ✓
}
```

The else-if block at line ~1018 correctly:
- ✅ Does NOT add `PART_COLOR_CODE` when the indicator is not present
- ✅ Has debug logging showing it enters this block

But it fails to:
- ❌ Add the `PART_SECTION_CODE` filter

This caused ACC rules filtered by PART_SECTION_CODE (like 'F28') to be missed.

## The Fix

### Code Changes
**File**: `ACCProcessingBatchDAO (1).java`  
**Line**: ~1027 (after the color code logic)

Added the missing PART_SECTION_CODE filter:

```java
}else if(!indicator.contains(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.PART_COLOR_CODE_CHANGE.value())){
    log.info("=== DEBUG: Entered else-if block (line 1018) ===");
    log.info("PART_COLOR_CODE_CHANGE NOT in indicators - should NOT add color code to query");
    
    if(StringUtils.equals(baseOrCurrentEventData, "BASE")){
        if(!previousEventPartDetails.getM_strPartColorCode().equals("")&& previousEventPartDetails.getM_strPartColorCode()!=null){
            querySB.append(" AND ACC.PART_COLOR_CODE= '" +previousEventPartDetails.getM_strPartColorCode()+"'");
        }
    } else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT")) {
        if(!currentEventPartDetails.getM_strPartColorCode().equals("")&& currentEventPartDetails.getM_strPartColorCode()!=null){
            querySB.append(" AND ACC.PART_COLOR_CODE= '" +currentEventPartDetails.getM_strPartColorCode()+"'");
        }
    } else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT_SAME")) {
        //Do nothing as we have to pick up the ACC for the Same in case any and also current.
    }
    //PSCC-5645 - End
    //MHC - due to multiple hierarchy change proc group needs to be removed as proc group can be changed from BOM maintenance
    //querySB.append(" AND ACC.PROC_SECT_CODE= '" +currentEventPartDetails.getM_strProcSectCode()+"'");
    
    // ✅ FIX: Add PART_SECTION_CODE filter to match ACC rules with appropriate part section
    querySB.append(" AND ACC.PART_SECTION_CODE= '" +currentEventPartDetails.getM_strPartSectionCode()+"'");
    log.info("Added PART_SECTION_CODE filter: " + currentEventPartDetails.getM_strPartSectionCode());
}
```

### Expected Behavior After Fix

When indicators `[2, 4]` are present (and PART_COLOR_CODE_CHANGE is not):
1. The query will **NOT** add `PART_COLOR_CODE` filter (correct behavior)
2. The query **WILL** add `PART_SECTION_CODE` filter (new behavior)
3. ACC rules stored with specific PART_SECTION_CODE values (like 'F28') will now be found

### Debug Output After Fix

```
=== DEBUG fetchACCDataForMultipleIndicatorChange START ===
Indicators: [2, 4]
baseOrCurrentEventData: BASE
Current Part Color Code: TYPE13
Previous Part Color Code: TYPE13
Indicators contains PART_COLOR_CODE_CHANGE: false
=== DEBUG: Entered else-if block (line 1018) ===
PART_COLOR_CODE_CHANGE NOT in indicators - should NOT add color code to query
Added PART_SECTION_CODE filter: F28
=== DEBUG fetchACCDataForMultipleIndicatorChange QUERY ===
SQL: ... AND ACC.PART_SECTION_CODE= 'F28' AND ACC.IS_BASE_OR_CURRENT_EVENT='B'
Query contains PART_COLOR_CODE: false
Query contains PART_SECTION_CODE: true
=== DEBUG fetchACCDataForMultipleIndicatorChange RESULT ===
Number of ACCs found: 1 (expected - was 0 before)
```

## Testing Recommendations

1. **Regression Testing**: Verify existing ACC queries still work correctly:
   - PROC_GROUP_CHANGE indicators
   - DESIGN_SECTION_CHANGE indicators
   - PART_COLOR_CODE_CHANGE indicators

2. **Specific Test Cases**:
   - Part: 87566HS2 A900
   - Supplier: 582804 → 578114
   - MTC Types: C01, A01, C06
   - Expected: Should now find ACC rules that were previously missed

3. **Validation**:
   - Compare ACC counts before and after the fix
   - Verify PART_SECTION_CODE values in ACC rules match part data
   - Check logs for "Added PART_SECTION_CODE filter" message

## Related Files
- `ACCProcessingBatchDAO (1).java` - Contains the fix
- `ACCProcessingBatchBO (1).java` - Calls the fixed method
- `ACCProcessingBatchSQLIF.java` - Defines the base SQL query

## Change History
- **Date**: 2026-05-31
- **Issue**: fetchACCDataForMultipleIndicatorChange returning 0 results
- **Fix**: Added PART_SECTION_CODE filter to else-if block handling non-color-code-change indicators
- **Impact**: Low risk - adds missing filter that aligns with other query paths
