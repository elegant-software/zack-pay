package com.elegant.software.blitzpay.merchant.api

/** An account deleted itself (GDPR Art. 17). Modules holding data keyed by [userId] (the JWT subject) erase it. */
data class AccountDeleted(val userId: String)
