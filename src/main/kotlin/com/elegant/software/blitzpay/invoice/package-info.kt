package com.elegant.software.blitzpay.invoice

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(
    displayName = "invoice",
    allowedDependencies = ["order :: OrderGateway", "merchant :: MerchantInvoiceGateway", "storage", "config"]
)
class ModuleMetadata
