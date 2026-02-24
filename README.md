I created this repo to hold code for a Test Automation error handling utility that utilizes AI both in creating it and to perform the analysis.  
This was created for a java-selenium stack and is intended to be compiled as a library for projects to call, but for now will be tested as if it were part of a real project.

THIS CODE IS UNDER TEST! I am trying to only have Claude make changes with me updating prompts and asking questions.
THERE ARE HALLUNICATIONS in this that I am trying to find and have Claude fix.  Usually I would fix myself but I am trying to have an AI genrated app.

This is a personal sandbox for me to learn to use AI to develop applications.  I envision this being done in three phases, which I'll document somewhere.  
I had Claude "build" the first two plus some refinements.  

As of Feb 20th, I have tests working that will pull from a local repository of known conditions.  Next steps are to show 
the details and turn those into local actions within the test framework to take corrective action.

Features to add (there are a lot, this is partial list for now):

1. Explicit logging messages on the root cause / insight and possible manual actions
2. Ability to read an automatic action from the kb / API that maps to a method
3. Cascading insights, e.g. on element not found, check for page timeout
4. Interface to external agents / MCP
5. A server based option residing within the firewall to store the knowledge repository and communicate with other services
6. 