I created this repo to hold code for a Test Automation error handling utility that utilizes AI both in creating it and to perform the analysis.  
This was created for a java-selenium stack and is intended to be compiled as a library for projects to call, but for now will be built and tested as if it were part of a real project.

THIS CODE IS UNDER TEST! I am trying to only have Claude make changes with me updating prompts and asking questions. Usually I would fix myself but I am trying to have an AI genrated app.

STATUS as of 25 Feb 2026

I switched from using Claude in a free-tier to using Claude code within my IDE.  Discovered API calls will cost me and updated the design to ue an "offline" mode and authenticating using my account through the IDE so I can test when it uses "live" mode.

Claude made the changes for the focus of the app to be using with a local knowledge base with AI API calls only made when manually enabled in the config and an API key is set.

Claude created unit tests and ran those tests, making changes to nearly every file.

Had Claude create a users document localed in root/docs folder.

Next steps:

1. [In-Progress] Review the unit tests and reporting myself, and make any functional changes
2. [In-Progress] Harden the ability to take specific actions based on a match.  These actions are bespoke to each application and not part of the core app code.  This also includes cascading checks, e.g. on element not found, check for page timeout.
3. Optimize the code
4. Experiment with live Claude calls to see what kind of insight it gives on what happened and what actions it recommends

Features to add (short-term). Claude Complete means I still need to review the output myself:

1. [Claude Complete] Logging of which insights were tripped and what actions taken, both at test and suite level.
2. [Claude Complete] When an element cannot be found, check if the page has finished loading.  If it is still loading throw a TimeoutException.
3. Mode to only call the AI engine manually once a human has reviewed the new insights
4. Circuit breaker / timeout in case the recovery engine gets into a loop

Future work depending on the result of Next Steps:

1. Use MCP or other protocol to connect to services like Dynatrace, Grafana, an others to perform detailed analysis on WHY a failure happened, e.g. dependent service was being re-deployed.
2. Add in local AI deployments, e.g. Claude running within a firewall and not Anthropics servers
