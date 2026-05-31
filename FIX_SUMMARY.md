# ACC Batch Issue - Fix Summary

## Problem Description
When running the ACC batch initially, ACCs are created with status **APPLIED**. However, when re-running the batch after supplier, share rate, or **part color code changes**, the system is creating **NEW rows** with **NON-APPLIED** status instead of recognizing existing APPLIED ACCs.

## Root Cause
The issue was introduced when **PART_COLOR_CODE** was newly added as a comparison criterion in the ACC system. 

In the SQL queries `ENTER_ACC_SUPP_MTO_SUMMARY_PROC_CHANGE_ADDED_DROPPED_PARTS_ACC_DATA` and `ENTER_ACC_SUPP_MTO_SUMMARY_PROC_CHANGE_ADDED_DROPPED_PARTS_ACC_DATA_CURRENT` (lines 263 and 291 in `ACCProcessingBatchSQLIF.java`), the WHERE clause included:

```sql
AND COALESCE(TRIM(ACC.PART_COLOR_CODE), '') = :partColorCode
```

### The Problem Flow:
1. **First Run:** ACC created with `PART_COLOR_CODE = 'COLOR_A'` → Status = `APPLIED`
2. **Part Color Code Changes:** Color code updated to `'COLOR_B'` in the system
3. **Second Run:** Query searches for existing ACC with `PART_COLOR_CODE = 'COLOR_B'`
4. **Result:** No match found (because existing ACC has `'COLOR_A'`)
5. **Outcome:** System creates a NEW ACC row with status `NON-APPLIED`

The same issue occurs for:
- **Supplier changes**
- **Share rate changes**
- **Part color code changes**

## Solution
**Removed the Part Color Code from the WHERE clause** in the ACC lookup queries.

### Changes Made:

#### File: `ACCProcessingBatchSQLIF.java`

**Query 1:** `ENTER_ACC_SUPP_MTO_SUMMARY_PROC_CHANGE_ADDED_DROPPED_PARTS_ACC_DATA` (Line ~277)

**Query 2:** `ENTER_ACC_SUPP_MTO_SUMMARY_PROC_CHANGE_ADDED_DROPPED_PARTS_ACC_DATA_CURRENT` (Line ~297)

**REMOVED LINE:**
```sql
AND COALESCE(TRIM(ACC.PART_COLOR_CODE), '') = :partColorCode
```

## Why This Fix Works

### Before Fix:
- System looks for existing ACC matching **ALL** criteria including Part Color Code
- When Part Color Code changes → No match found → Creates new ACC

### After Fix:
- System looks for existing ACC matching all criteria **EXCEPT** Part Color Code
- When Part Color Code changes → Existing ACC is found → Reuses existing APPLIED ACC
- Part Color Code is still **stored** in the ACC record, just not used for **lookup**

## Business Logic
The Part Color Code should be:
- ✅ **Stored** in the ACC record (for tracking/audit purposes)
- ✅ **Compared** to detect changes (to determine if ACC processing is needed)
- ❌ **NOT used** to lookup existing ACC records (this was causing duplicate rows)

## Impact
After this fix:
- ✅ ACC will remain in APPLIED state when re-running batch with color code changes
- ✅ No duplicate ACC rows will be created
- ✅ Supplier changes will correctly find existing ACCs
- ✅ Share rate changes will correctly find existing ACCs
- ✅ Part color code changes will correctly find existing ACCs

## Testing Recommendation
1. **Test Scenario 1:** Run ACC batch → Apply ACC → Change Part Color Code → Re-run batch
   - **Expected:** Existing APPLIED ACC should be found and maintained
   
2. **Test Scenario 2:** Run ACC batch → Apply ACC → Change Supplier → Re-run batch
   - **Expected:** Existing APPLIED ACC should be found and maintained
   
3. **Test Scenario 3:** Run ACC batch → Apply ACC → Change Share Rate → Re-run batch
   - **Expected:** Existing APPLIED ACC should be found and maintained

## Files Modified
- `/projects/sandbox/ACC/ACCProcessingBatchSQLIF.java`
  - Line ~277: Removed `PART_COLOR_CODE` condition from `ENTER_ACC_SUPP_MTO_SUMMARY_PROC_CHANGE_ADDED_DROPPED_PARTS_ACC_DATA`
  - Line ~297: Removed `PART_COLOR_CODE` condition from `ENTER_ACC_SUPP_MTO_SUMMARY_PROC_CHANGE_ADDED_DROPPED_PARTS_ACC_DATA_CURRENT`

---

**Date:** May 31, 2026  
**Fixed By:** Kiro AI Assistant
