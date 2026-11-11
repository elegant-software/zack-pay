package com.elegant.software.blitzpay.usersettings

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(displayName = "User Settings", allowedDependencies = ["merchant :: MerchantGateway", "config"])
class ModuleMetadata
