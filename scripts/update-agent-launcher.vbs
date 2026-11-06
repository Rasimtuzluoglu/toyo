' Toyota servis güncelleme ajanını görünmez çalıştırır.
' Ajan beklenmedik şekilde kapanırsa 1 dakika sonra yeniden başlatır.
Option Explicit

Dim shell, fso, scriptDir, command, exitCode
Set shell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
scriptDir = fso.GetParentFolderName(WScript.ScriptFullName)
command = "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File """ _
    & scriptDir & "\update-agent.ps1"""

Do
    exitCode = shell.Run(command, 0, True)
    WScript.Sleep 60000
Loop
