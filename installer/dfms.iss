; Inno Setup script for the DFMS app (free replacement for install4j).
; Built by .github/workflows/release.yml as:
;   iscc /DAppVersion=1.1.0 installer\dfms.iss
; Input is the jpackage app-image in target\jpackage\DFMS (app + its own Java runtime).
; This installer does NOT install MySQL - it is the small "update" installer that the app downloads
; and opens from the login screen, and it also works for a first install on a PC that
; already has MySQL. Data lives in MySQL, so installing over an old version never touches it.

#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif
#define AppName "Daily Finance Management System"
#define AppExe "DFMS.exe"

[Setup]
; Never change AppId - it is how Windows knows a new version replaces the old one.
AppId={{8F3C2A51-6B1D-4E7A-9C2F-DF5A1B7E3C10}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
; first install suggests this; every later run suggests the folder it was installed in last time
DefaultDirName={autopf}\DFMS
DisableProgramGroupPage=yes
; always ask where to install (the auto-update opens this wizard too)
DisableDirPage=no
UsePreviousAppDir=yes
OutputDir=..\target\installer
OutputBaseFilename=DFMS-Update-{#AppVersion}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
; close a running DFMS before replacing its files (the app also exits itself before updating)
CloseApplications=force
RestartApplications=no
UninstallDisplayIcon={app}\{#AppExe}
; installer's own icon - dfms.ico is made from src/main/resources/images/logo.png
SetupIconFile=dfms.ico

[InstallDelete]
; drop only the previous version's Java runtime so no stale files are left behind. Nothing else is
; deleted - credentials.json (copied by hand next to DFMS.exe) and any other user file survive
; every update; app\dfms.jar is simply overwritten.
Type: filesandordirs; Name: "{app}\runtime"

[Files]
Source: "..\target\jpackage\DFMS\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Dirs]
; the app writes files (e.g. the Google Drive "tokens" folder) to its working directory, and
; Program Files is not writable for a normal user - so it runs from here instead
Name: "{localappdata}\DFMS"

[Icons]
Name: "{autoprograms}\DFMS"; Filename: "{app}\{#AppExe}"; WorkingDir: "{localappdata}\DFMS"
Name: "{autodesktop}\DFMS"; Filename: "{app}\{#AppExe}"; WorkingDir: "{localappdata}\DFMS"

[Run]
; "Start DFMS" checkbox on the last page; runs as the normal user, not as admin
Filename: "{app}\{#AppExe}"; WorkingDir: "{localappdata}\DFMS"; Description: "Start DFMS"; Flags: nowait postinstall runasoriginaluser
