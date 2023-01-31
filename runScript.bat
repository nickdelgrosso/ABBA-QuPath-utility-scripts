@echo off
@REM This should be where QuPath is installed.
SET "PATH=D:\Programs\QuPath;%PATH%"

"QuPath-0.4.2 (console).exe" help script

@echo on
"QuPath-0.4.2 (console).exe" script cells_in_regions.groovy -p "D:\QuPath Projects\Project1\project.qpproj"