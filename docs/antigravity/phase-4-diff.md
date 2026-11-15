# Phase 4 Diff — Workflows & Sharing

Phase 4 implements the Markdown workflow format (YAML front-matter + `{{placeholder=default}}` tokens + H1 fallback parsing), atomic filesystem repository persistence, Android Share Intent export and incoming file/text workflow detection, and the unified Compose workflow picker, parameter-fill, and editor sheets.

## Added Files

- [`Workflow.kt`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/newtermux/workflows/Workflow.kt): `WorkflowToken` and `Workflow` data model with `extractTokens()` and `renderCommand()`.
- [`WorkflowMarkdownParser.kt`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/newtermux/workflows/WorkflowMarkdownParser.kt): parses YAML front-matter (`name`, `description`, `tags`, `author`, `version`) + fenced command blocks, supports H1 Markdown fallback parsing, resolves `{{placeholder=default}}` tokens, and provides `slugify()`.
- [`WorkflowRepository.kt`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/newtermux/workflows/WorkflowRepository.kt): persistent file store under `filesDir/workflows/<id>.md` with atomic reload, save, Markdown import, delete, and file lookup across restarts.
- [`WorkflowShareHelper.kt`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/newtermux/workflows/WorkflowShareHelper.kt): builds `ACTION_SEND` + `ACTION_CHOOSER` shares backed by `FileProvider` (`${applicationId}.fileprovider`) and `EXTRA_TEXT`, plus `readBoundedBytes` and `inspectIncomingBytes` for incoming workflow detection.
- [`WorkflowSheets.kt`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/newtermux/workflows/WorkflowSheets.kt): Compose `WorkflowSheetOverlay`, `WorkflowPickerContent`, `WorkflowListCard`, `WorkflowParamFillContent`, and `WorkflowEditorContent`.
- [`WorkflowHostBridge.kt`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/newtermux/workflows/WorkflowHostBridge.kt): attaches the workflow Compose overlay to `android.R.id.content` and connects `BlockHostBridge.setSaveWorkflowHandler` and `InputHostBridge.setOpenWorkflowsHandler`.
- [`WorkflowParserAndStoreTest.kt`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/test/java/com/newtermux/workflows/WorkflowParserAndStoreTest.kt): unit and Compose UI tests covering YAML front-matter persistence across restarts, H1 fallback parsing, Share Intent export/import round-trip, and picker/param-fill/editor UI flows.

## Modified Files

- [`WarpComponents.kt`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/newtermux/theme/WarpComponents.kt): rendered `WarpBottomSheet` via an unclipped `Column` with background/border corners so asymmetric rounded top corners preserve accurate pointer hit-testing inside Compose overlays.
- [`BlockHostBridge.kt`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/newtermux/blocks/BlockHostBridge.kt): added `setSaveWorkflowHandler` and wired block `"Save Workflow"` actions to `BlockWorkspaceHost`.
- [`TermuxActivity.java`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/termux/app/TermuxActivity.java): attached `mWorkflowHostBridge` in `onCreate`, exposed `getWorkflowHostBridge()`, and reloaded the workflow repository in `onResume`.
- [`FileReceiverActivity.java`](file:///data/data/com.termux/files/home/projects/BeTermux/app/src/main/java/com/termux/app/api/file/FileReceiverActivity.java): added workflow Markdown inspection (`handleIncomingStreamWithWorkflowCheck`, `shouldInspectStreamForWorkflow`, `promptWorkflowImportOrSave`) across shared text, content URIs, and file URIs while preserving URL handling.
