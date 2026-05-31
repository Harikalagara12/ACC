# ACC Duplicate Row Issue - Flow Analysis

## 🎯 THE ACTUAL PROBLEM LOCATION

After deep analysis, I found the **REAL culprit** causing duplicate rows when ACC is reapplied with color code/supplier/share rate changes.

---

## 📍 **EXACT LOCATION OF THE BUG:**

**File:** `ACCProcessingBatchDAO (1).java`  
**Method:** `fetchACCDataForMultipleIndicatorChange()`  
**Lines:** 1018-1029  

---

## 🔍 **THE PROBLEMATIC CODE:**

```java
}else if(!indicator.contains(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.PART_COLOR_CODE_CHANGE.value())){
    
    if(StringUtils.equals(baseOrCurrentEventData, "BASE")){
        if(!previousEventPartDetails.getM_strPartColorCode().equals("")&& previousEventPartDetails.getM_strPartColorCode()!=null){
            querySB.append(" AND ACC.PART_COLOR_CODE= '" +previousEventPartDetails.getM_strPartColorCode()+"'");  // ❌ PROBLEM!
        }
    } else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT")) {
        if(!currentEventPartDetails.getM_strPartColorCode().equals("")&& currentEventPartDetails.getM_strPartColorCode()!=null){
            querySB.append(" AND ACC.PART_COLOR_CODE= '" +currentEventPartDetails.getM_strPartColorCode()+"'");  // ❌ PROBLEM!
        }
    }
}
```

---

## 🌊 **THE FLOW CAUSING DUPLICATES:**

### **Scenario: Multiple Indicators (Supplier Change + Share Rate Change)**

#### **Run 1: Initial ACC Creation**
```
Step 1: User runs ACC batch
├─ Part: 87566-HS2 A800
├─ Supplier: 582804 (NIPPON CARBIDE)
├─ Color Code: TYPE28
├─ Share Rate: 0.0000
└─ Indicators: [SUPPLIER_CHANGE, SHARE_RATE_CHANGE]

Step 2: System calls fetchACCDataForMultipleIndicatorChange()
├─ indicator.contains("PART_COLOR_CODE_CHANGE") → FALSE
├─ Line 1018: Enters else-if block
└─ Line 1024: Adds: AND ACC.PART_COLOR_CODE = 'TYPE28'

Step 3: Query to FCACC1 table
├─ WHERE ... AND PART_COLOR_CODE = 'TYPE28'
├─ Result: No existing ACC found
└─ Creates NEW ACC with TYPE28 → Status = APPLIED ✓
```

#### **Run 2: Color Code Changes to TYPE13**
```
Step 1: Color code changes TYPE28 → TYPE13 in source data
├─ Part: 87566-HS2 A800 (same)
├─ Supplier: 582804 (same) 
├─ Color Code: TYPE13 (CHANGED!)
├─ Share Rate: 0.0000 (same)
└─ Indicators: [SUPPLIER_CHANGE, SHARE_RATE_CHANGE]  ← NO COLOR_CODE_CHANGE indicator!

Step 2: User reruns ACC batch
├─ System calls fetchACCDataForMultipleIndicatorChange()
├─ indicator.contains("PART_COLOR_CODE_CHANGE") → FALSE (still!)
├─ Line 1018: Enters else-if block again
└─ Line 1024: Adds: AND ACC.PART_COLOR_CODE = 'TYPE13'  ← NEW VALUE!

Step 3: Query to FCACC1 table
├─ WHERE ... AND PART_COLOR_CODE = 'TYPE13'
├─ Result: NO MATCH! (existing ACC has TYPE28)
└─ Creates NEW ACC with TYPE13 → Status = NON-APPLIED ❌

DATABASE STATE:
┌──────────────────────────────────────────────────────┐
│ Row 1: TYPE28 | APPLIED    ← Orphaned                │
│ Row 2: TYPE13 | NON-APPLIED ← Duplicate!             │
└──────────────────────────────────────────────────────┘
```

---

## 🚨 **WHY THIS HAPPENS:**

1. **Line 1018 Logic Flaw:**
   ```java
   if(!indicator.contains("PART_COLOR_CODE_CHANGE"))
   ```
   This condition says: "If PART_COLOR_CODE_CHANGE is NOT in the indicator list, then ADD color code to WHERE clause"

2. **The Trap:**
   - When user has ONLY Supplier Change + Share Rate Change
   - Color code changes "silently" in background (TYPE28 → TYPE13)
   - Indicator list does NOT include "PART_COLOR_CODE_CHANGE"
   - Line 1018 condition = TRUE → Adds PART_COLOR_CODE to WHERE clause
   - Query searches for NEW color (TYPE13)
   - Can't find old ACC (has TYPE28)
   - Creates duplicate!

3. **Why Part Added/Dropped Methods Are NOT the Issue:**
   - Those methods (`fetchACCDataForProcChangePartAddedDropped`) use static SQL queries
   - The SQL queries were fixed (removed PART_COLOR_CODE from WHERE clause)
   - They don't have dynamic indicator-based logic
   - **They are working correctly!**

---

## ✅ **THE FIX:**

**Removed the entire PART_COLOR_CODE checking block from line 1018-1029:**

```java
}else if(!indicator.contains(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.PART_COLOR_CODE_CHANGE.value())){
    
    // FIX: Removed PART_COLOR_CODE from WHERE clause to prevent duplicate ACC rows
    // when color code changes (e.g., TYPE28 → TYPE13)
    // Part color code is still stored in ACC record, just not used for lookup
    
    // ORIGINAL CODE (REMOVED) - Lines 1020-1027
    
}else {
```

---

## 🎯 **WHY THE FIX WORKS:**

### **After Fix - Run 2 Behavior:**
```
Step 1: Color code changes TYPE28 → TYPE13
Step 2: User reruns ACC batch
Step 3: System calls fetchACCDataForMultipleIndicatorChange()
├─ Lines 1020-1027: REMOVED (no color code check)
└─ Query: WHERE ... (no PART_COLOR_CODE condition)

Step 4: Query to FCACC1 table
├─ WHERE base_event, current_event, part_no, supplier, etc.
├─ NO COLOR CODE CHECK!
├─ Result: FOUND Row 1 (with TYPE28) ✓
└─ REUSES existing APPLIED ACC → Updates to TYPE13 ✓

DATABASE STATE:
┌──────────────────────────────────────────────────────┐
│ Row 1: TYPE13 | APPLIED    ← Updated, no duplicates! │
└──────────────────────────────────────────────────────┘
```

---

## 📊 **COMPARISON OF METHODS:**

| Method | Issue? | Fixed? | Notes |
|--------|--------|--------|-------|
| `fetchACCData` | ❌ NO | N/A | Only adds PART_COLOR_CODE when match type = "PART_COLOR_CODE_CHANGE_MATCH" (correct behavior) |
| `fetchACCDataForMultipleIndicatorChange` | ✅ YES | ✅ YES | **This was the culprit!** Lines 1018-1029 removed |
| `fetchACCDataForUnMatched` | ❌ NO | N/A | Doesn't use PART_COLOR_CODE at all |
| `fetchACCDataForProcChangePartAddedDropped` | ❌ NO | ✅ REVERTED | SQL fix already applied, no Java changes needed |

---

## 🧪 **TEST SCENARIO:**

### **To Reproduce the Original Bug:**
1. Run ACC batch with part that has COLOR=TYPE28
2. Apply the ACC (status = APPLIED)
3. Change color code to TYPE13 in source data
4. Do NOT change anything else (keep same supplier, share rate)
5. Rerun ACC batch

**Before Fix:**
- Creates NEW NON-APPLIED ACC with TYPE13
- Old APPLIED ACC with TYPE28 remains (orphaned)
- Result: 2 rows for same part!

**After Fix:**
- Finds existing APPLIED ACC (ignores color code)
- Reuses existing ACC
- Result: 1 row, properly maintained!

---

## 📋 **FILES MODIFIED:**

### 1. `ACCProcessingBatchDAO (1).java`
**Location:** Lines 1018-1029  
**Method:** `fetchACCDataForMultipleIndicatorChange()`  
**Change:** Removed PART_COLOR_CODE dynamic WHERE clause addition

### 2. `ACCProcessingBatchSQLIF.java`
**Location:** Lines 277, 297  
**Queries:** `ENTER_ACC_SUPP_MTO_SUMMARY_PROC_CHANGE_ADDED_DROPPED_PARTS_ACC_DATA` and `_CURRENT` variant  
**Change:** Removed static `COALESCE(TRIM(ACC.PART_COLOR_CODE), '') = :partColorCode` condition

---

## 💡 **KEY LEARNINGS:**

1. ✅ **Part Added/Dropped methods are NOT the problem** - already fixed by SQL changes
2. ✅ **Multiple indicators method was the culprit** - dynamic code adding PART_COLOR_CODE
3. ✅ **The bug triggers when:**
   - Multiple indicators present (Supplier Change + Share Rate Change)
   - Color code changes in background
   - NO explicit PART_COLOR_CODE_CHANGE indicator
4. ✅ **Color code should be:**
   - Stored in ACC record (yes)
   - Used for comparison/detection (yes)
   - Used for lookup/matching (NO - this was the bug!)

---

**Analysis Date:** May 31, 2026  
**Analyzed By:** Kiro AI Assistant  
**Root Cause:** Dynamic PART_COLOR_CODE addition in `fetchACCDataForMultipleIndicatorChange()` method
