# AR Remote Assistance
## Background
Remote assistance is a necessity in the daily lives of many people. Used in trainings, technical support, or daily-life support to provide people with disabilities and older adults with greater independence.
However, for years, it has been limited to the audio and video provided by video call services. That's why integrating an AR (Augmented Reality) environment into the sessions would greatly improve communication.
## What is offered
In this repository, you will find a native Android (Java) mobile application where you can join calls as an assistance provider or assistance requester.
When you join as the remote assistant, you can view the assistee's environment through their camera and yourself through your camera. You will also be given a set of shapes that you can add to the assistee's environment with a tap. Additionally, you can change the size of the chosen element.
As the assistee, on the top-left of the screen, you will be able to see the assistant through their front camera. Elements will be added to your environment by the assistant to better guide you through a process.

## How it is developed
For the RTC service, Agora (v. 4.1.1), an RTC wrapper, was the provider of choice.
ARCore (v. 1.36.0) is parallely used to build AR environments using GLSurfaces.
The assets were created on Vectary.com.

## How to use it
To run the project you need:
- Android Studio
- An Agora account (the app ID)

From the Agora console, create a new project to get an app ID. From the project settings, generate a temporary token with the channel name of your choice (don't forget to copy the token).
Fill the mentioned fields in RemoteAssistance/app/src/main/java/com/example/remoteassistance/CustomUtilities.java with the data you generated.

This application has been developed to be part of https://github.com/SteevenAlbert/Arabeitak-Native-Android. 
Inspired by https://github.com/AgoraIO-Community/ARCoreAgora
