package com.nani.agent.policy

enum class AgentCapability {
    ReadFiles,
    WriteFiles,
    CreateFiles,
    CopyFiles,
    CreateFolders,
    RenameFiles,
    ReadScreen,
    ClickUi,
    TypeText,
    Scroll,
    OpenAllowedApps,
    UseLocalApps,
    DeleteFiles,
    OpenSettings,
    ChangePermissions,
    InstallApps,
    UninstallApps,
    RootActions,
    DeviceAdmin,
    Overlay,
    AccessMainUserProfile,
    UseBrowser,
    UseInternet,
    OpenUrl,
    SendNetworkRequest,
    UploadFile,
    ShareFileOutsideDevice,
    SendMessage,
    SendEmail
}

object AgentCapabilities {
    val allowedByDefault = setOf(
        AgentCapability.ReadFiles,
        AgentCapability.WriteFiles,
        AgentCapability.CreateFiles,
        AgentCapability.CopyFiles,
        AgentCapability.CreateFolders,
        AgentCapability.RenameFiles,
        AgentCapability.ReadScreen,
        AgentCapability.ClickUi,
        AgentCapability.TypeText,
        AgentCapability.Scroll,
        AgentCapability.OpenAllowedApps,
        AgentCapability.UseLocalApps
    )

    val forbiddenAlways = setOf(
        AgentCapability.DeleteFiles,
        AgentCapability.OpenSettings,
        AgentCapability.ChangePermissions,
        AgentCapability.InstallApps,
        AgentCapability.UninstallApps,
        AgentCapability.RootActions,
        AgentCapability.DeviceAdmin,
        AgentCapability.Overlay,
        AgentCapability.AccessMainUserProfile
    )

    val requiresExplicitConfirmationEveryTime = setOf(
        AgentCapability.UseBrowser,
        AgentCapability.UseInternet,
        AgentCapability.OpenUrl,
        AgentCapability.SendNetworkRequest,
        AgentCapability.UploadFile,
        AgentCapability.ShareFileOutsideDevice,
        AgentCapability.SendMessage,
        AgentCapability.SendEmail
    )
}
