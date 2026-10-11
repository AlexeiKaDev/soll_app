# Approval-first suggestion actions

Home's ScenarioDetector and SuggestionEngine already produce proposals without executing device actions, and HomeScreen exposes an explicit accept button. ActionLoop now stores the currently visible proposals and atomically consumes a proposal on acceptance. Unknown, withdrawn and already accepted IDs cannot execute. Refreshing proposals does not re-arm an accepted ID during the lifetime of the view model.

HomeViewModel submits its existing proposal list and uses ActionLoop acceptance before feedback, memory and device-action callbacks. Existing SettingsRepository feedback persists suppression across sessions; pending proposals are regenerated from current signals on recreation rather than serializing stale device state. No backend permissions, device commands or automatic acceptance are added. Acceptance consumes before side effects; this is at-most-once dispatch within the view lifetime, not a durable exactly-once guarantee for external effects.

Targeted ActionLoop and ProactiveSuggestions tests: five passed, Android debug Kotlin/Java compilation passed. Tests cover no action from proposal submission, one explicit acceptance, repeated clicks, refresh after acceptance and withdrawn proposals. No physical-device acceptance or APK installation is claimed.
