package com.elegant.software.blitzpay.order

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(
    displayName = "order",
    allowedDependencies = ["merchant :: MerchantGateway", "appointments :: appointments-api", "shopper :: ShopperProfileGateway", "config"]
)
class ModuleMetadata
