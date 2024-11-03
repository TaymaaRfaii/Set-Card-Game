# Set-Card-Game
Here's a README template for your "Set Card Game" project, focusing on concurrency and synchronization in Java.

---

# Set Card Game

This project is an implementation of the "Set" card game with a focus on **Java Concurrency** and **Synchronization**. It’s designed to help practice concurrent programming concepts while building a multiplayer card game that supports both human and non-human players. The game uses Java Threads and various synchronization mechanisms to manage game flow and ensure fair interactions among players.

## Table of Contents
- [Overview](#overview)
- [Game Rules](#game-rules)
- [Design and Components](#design-and-components)
- [Features](#features)
- [Configuration](#configuration)
- [Grading and Bonus Challenges](#grading-and-bonus-challenges)

## Overview
The project requires implementing a version of the "Set" card game that runs with multiple threads representing different players. This game is part of a university assignment to learn and apply concurrency principles in Java. The user interface and some components, such as the keyboard input manager and graphical updates, have been pre-written, allowing you to focus on game logic and synchronization.

## Game Rules
The goal of the game is to find a "Set" of three cards that meet specific criteria:
1. Each card has four features: color, number, shape, and shading.
2. For each feature, the cards in a set must either be all the same or all different.
3. Players gain points by correctly identifying sets and lose points for incorrect selections.

A legal set example:
- Three cards with different colors, shapes, and shadings but the same number.

## Design and Components

### Game Components
- **Deck**: Contains 81 cards, each with unique combinations of features.
- **Table**: A 3x4 grid displaying cards drawn from the deck.
- **Players**: Represented as threads, where human players input moves via the keyboard, and non-human players simulate moves.
- **Dealer**: Manages the game flow, deals cards, checks sets, awards points, and enforces penalties.

### Key Classes
- `Dealer.java`: Controls game events and interacts with players.
- `Player.java`: Manages player actions and synchronization.
- `Table.java`: Represents the shared data structure between players and the dealer.
- `UserInterface.java`: Handles graphical updates.
## Features
- **Multiplayer (Human and Non-human)**: Supports both human input and automated players.
- **Concurrency and Synchronization**: Uses Java threads and synchronization primitives to manage player actions.
- **Penalty System**: Players who incorrectly declare a set receive a temporary penalty.
- **Dynamic Table Update**: Reshuffles the table if no legal sets are available.

## Configuration
Game settings are loaded from a `config.properties` file, allowing you to adjust parameters like the number of players, timeout durations, and penalty settings.

## Grading and Bonus Challenges
The assignment includes specific grading criteria and bonus points for:
1. Full support for configuration options.
2. Graceful thread termination.
3. Handling timers for user interactions.
4. Minimizing unnecessary thread wake-ups to optimize efficiency.
