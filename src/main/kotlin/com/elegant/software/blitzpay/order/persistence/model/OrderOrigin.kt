package com.elegant.software.blitzpay.order.persistence.model

/** Where an Order was placed: [KIOSK] on a restaurant-owned Kiosk, [STANDARD] everywhere else. */
enum class OrderOrigin { STANDARD, KIOSK }
