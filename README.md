Azot
====
_Originally written for_ **Geneviève CHAZOT** _, a fantastic Technical Writer and a Woman :)_ 


Overview
---
Azot is a tool which help to execute easily a set of HTTP calls in a workflow.

An Azot workflow is defined in a XML file (syntax will be described later in this wiki page).

Azot is available as an executable JAR (azot.jar) which can also be used as an Ant task. Azot requires Java6 installed on the computer where you will use it.

To launch Azot as an executable, you just need to provide the workflow file as a first argument:

    java -jar azot.jar myworkflow.xml

[How to write an Azot workflow?](Workflow-XML-description)
