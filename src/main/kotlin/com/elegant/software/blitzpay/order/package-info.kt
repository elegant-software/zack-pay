package com.elegant.software.blitzpay.order

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(
    displayName = "order",
    allowedDependencies = ["merchant :: MerchantGateway", "payments.push :: push-api", "appointments :: appointments-api", "shopper :: ShopperProfileGateway"]
)
class ModuleMetadata
