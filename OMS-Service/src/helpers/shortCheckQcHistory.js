const { RmaRequestStatusHistory } = require('../models/seqModels/index');

/**
 * Derives aggregate verification status from WMS short-check orderItems.
 * - status_message: only for Verification_Failed or Partially_Verified — one qcReason (first FAIL with a reason).
 * - status: Received (no fail message) | Verification_Failed | Partially_Verified
 *
 * @param {Array<{ qcStatus?: string, qcReason?: string }>} reqItems
 * @returns {{ status: string, status_message: string|null }|null}
 */
const deriveShortCheckVerificationHistory = reqItems => {
  if (!reqItems?.length) {
    return null;
  }

  let pass = 0;
  let fail = 0;
  let unknown = 0;
  let firstFailReason = null;

  for (const r of reqItems) {
    const s = String(r.qcStatus ?? '')
      .trim()
      .toUpperCase();
    if (s === 'PASS') {
      pass++;
    } else if (s === 'FAIL' || s === 'FAILED') {
      fail++;
      if (firstFailReason == null && r.qcReason) {
        firstFailReason = String(r.qcReason).trim();
      }
    } else {
      unknown++;
    }
  }

  const n = reqItems.length;

  let status;
  if (fail === 0 && pass === n) {
    status = 'Received';
  } else if (pass === 0 && fail === n) {
    status = 'Verification_Failed';
  } else if (fail > 0 && pass > 0) {
    status = 'Partially_Verified';
  } else if (fail > 0) {
    status = 'Partially_Verified';
  } else {
    status = 'Received';
  }

  const status_message =
    status === 'Verification_Failed' || status === 'Partially_Verified'
      ? firstFailReason || null
      : null;

  return { status, status_message };
};

/**
 * Persists short-check QC verification summary to amasty_rma_request_status_history.
 * Fails softly (logs only) so short-check API still succeeds.
 */
const saveShortCheckQcHistory = async ({
  requestId,
  rmaIncrementId,
  reqItems,
  waybill = null
}) => {
  const derived = deriveShortCheckVerificationHistory(reqItems);
  if (!derived) {
    return;
  }

  try {
    await RmaRequestStatusHistory.create({
      request_id: Number(requestId),
      reference_number: rmaIncrementId != null ? String(rmaIncrementId) : null,
      status: derived.status,
      status_message: derived.status_message,
      created_at: new Date(),
      notification_event_id: 'short_check_v2',
      waybill: waybill || null
    });
  } catch (e) {
    global.logError(e, {
      msg: 'amasty_rma_request_status_history short-check create failed',
      rmaIncrementId,
      requestId
    });
  }
};

module.exports = {
  deriveShortCheckVerificationHistory,
  saveShortCheckQcHistory
};
