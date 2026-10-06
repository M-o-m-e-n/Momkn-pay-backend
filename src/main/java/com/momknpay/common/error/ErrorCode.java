package com.momknpay.common.error;

/**
 * Every error the API can return (SRS §4.4). Clients switch on the name, never on the messages. A
 * new failure case needs a new code here, in the SRS and in the OpenAPI spec.
 */
public enum ErrorCode {
    VALIDATION_ERROR(400, "Invalid input.", "مدخلات غير صحيحة."),
    DECRYPTION_FAILED(400, "The secure payload could not be read.", "تعذر قراءة البيانات المشفرة."),
    INSUFFICIENT_BALANCE(
            402, "Payment declined: insufficient balance.", "تم رفض الدفع: الرصيد غير كافٍ."),
    USER_NOT_FOUND(404, "User not found.", "المستخدم غير موجود."),
    SESSION_NOT_FOUND(404, "Session not found.", "الجلسة غير موجودة."),
    SERVICE_NOT_FOUND(404, "Service not found.", "الخدمة غير موجودة."),
    SUBSCRIBER_NOT_FOUND(404, "No bill found for this number.", "لا توجد فاتورة لهذا الرقم."),
    INQUIRY_NOT_FOUND(404, "Inquiry not found.", "الاستعلام غير موجود."),
    TRANSACTION_NOT_FOUND(404, "Transaction not found.", "المعاملة غير موجودة."),
    NOT_FOUND(404, "Resource not found.", "المورد غير موجود."),
    METHOD_NOT_ALLOWED(405, "Method not allowed.", "الطريقة غير مسموح بها."),
    BILL_ALREADY_PAID(409, "This bill is already paid.", "تم سداد هذه الفاتورة بالفعل."),
    EMAIL_ALREADY_USED(409, "This email is already in use.", "البريد الإلكتروني مستخدم بالفعل."),
    IDEMPOTENCY_CONFLICT(
            409,
            "Idempotency key already used for another inquiry.",
            "مفتاح التكرار مستخدم لاستعلام آخر."),
    INQUIRY_ALREADY_CONFIRMED(
            409, "This inquiry has already been paid.", "تم دفع هذا الاستعلام بالفعل."),
    SESSION_EXPIRED(410, "Your secure session has expired.", "انتهت صلاحية الجلسة الآمنة."),
    INQUIRY_EXPIRED(410, "This inquiry has expired.", "انتهت صلاحية الاستعلام."),
    INQUIRY_INVALIDATED(
            410,
            "Too many wrong PIN attempts. Start a new inquiry.",
            "محاولات رقم سري خاطئة كثيرة. ابدأ استعلامًا جديدًا."),
    AMOUNT_OUT_OF_RANGE(
            422,
            "The amount is outside the allowed range for this service.",
            "المبلغ خارج الحد المسموح لهذه الخدمة."),
    RATE_LIMITED(
            429, "Too many attempts. Try again shortly.", "محاولات كثيرة. حاول مرة أخرى بعد قليل."),
    INTERNAL_ERROR(500, "Something went wrong.", "حدث خطأ ما."),
    SERVICE_UNAVAILABLE(
            503, "The provider is not responding right now.", "مزود الخدمة لا يستجيب حاليًا.");

    private final int status;
    private final String messageEn;
    private final String messageAr;

    ErrorCode(int status, String messageEn, String messageAr) {
        this.status = status;
        this.messageEn = messageEn;
        this.messageAr = messageAr;
    }

    public int status() {
        return status;
    }

    public String messageEn() {
        return messageEn;
    }

    public String messageAr() {
        return messageAr;
    }
}
