I created this repo to hold code for a Test Automation error handling utility that utilizes AI both in creating it and to perform the analysis.  
This was created for a java-selenium stack and is intended to be compiled as a library for projects to call, but for now will be tested as if it were part of a real project.

THIS CODE IS UNDER TEST! I am trying to only have Claue make changes with me updating prompts and asking questions.
THERE ARE HALLUNICATIONS in this that I am trying to find and have Claude fix.  Usually I would fix myself but I am trying to have an AI genrated app.

This is a personal sandbox for me to learn to use AI to develop applications.  I envision this being done in three phases, which I'll document somewhere.  
I had Claude "build" the first two plus some refinements.  As of 2/18/2026 I have not manually reviewed the code and made changes to it, so again if you found this
it is a sandbox for me to learn the nuance of AI generated code.

As of Feb 19th, merged in phase 1 and 2 plus two key features: condition when the anomaly is not  problem (e.g. you bypassed the login screen), and a knowledge base so we do not have to always hit the API for every known condition.

I an starting the code cleanup, test and debug process using a local file with known conditions first to save on the cost of API tokens.
