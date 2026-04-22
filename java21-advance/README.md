# Java 21 Advanced Core Engineering

Training course materials for Advanced Core Java Engineering using Java 21 LTS.

---

## Prerequisites

Before the training day, make sure the following are installed and working on your machine.

### 1. Enable Hardware Virtualisation

Docker requires hardware virtualisation to be enabled at the BIOS/firmware level.

**Windows**

- Restart your machine and enter BIOS (usually `F2`, `F10`, `DEL` or `ESC` on startup)
- Look for **Intel VT-x** or **AMD-V** (may be listed as Virtualisation Technology)
- Enable it and save
- Back in Windows, open Task Manager → Performance → CPU
- Confirm **Virtualisation: Enabled**

**Mac (Intel)**

- Virtualisation is enabled by default — no action needed

**Mac (Apple Silicon — M1/M2/M3)**

- Virtualisation is enabled by default — no action needed

---

### 2. Install Docker Desktop

- Download from [https://www.docker.com/products/docker-desktop](https://www.docker.com/products/docker-desktop)
- Install and start Docker Desktop
- Confirm it is running — you should see the Docker whale icon in your system tray (Windows) or menu bar (Mac)
- Open a terminal and verify:

```bash
docker version
```

You should see both Client and Server information.

---

### 3. Install Visual Studio Code

- Download from [https://code.visualstudio.com](https://code.visualstudio.com)
- Install the **Dev Containers** extension:
  - Open VSCode
  - Go to Extensions (`Ctrl+Shift+X` / `Cmd+Shift+X`)
  - Search for `Dev Containers` by Microsoft
  - Install it

---

## Getting Started

https://onedrive.live.com/?redeem=aHR0cHM6Ly8xZHJ2Lm1zL2YvYy9lYmUzZWE4MTQ2Yjg0OWUzL0lnQWpjSXRmdDRmOFJibkZMV0p2T2VMRkFkOVBMVW1fVzRhdV81Q3NCSFpnM2Z3P2U9czVBUW5F&id=EBE3EA8146B849E3%21s5f8b702387b745fcb9c52d626f39e2c5&cid=EBE3EA8146B849E3

### 1. Clone this repository

```bash
git clone https://github.com/richard-learning/java21-advance.git
cd java21-advance
```

### 2. Open in VSCode

```bash
code .
```

### 3. Reopen in Container

VSCode will detect the devcontainer configuration and show a prompt in the bottom right:

> **"Reopen in Container"**

Click it. Docker will pull the course image and set everything up automatically.

First time will take a few minutes depending on your internet connection. Subsequent opens are instant.

### 4. Verify your environment

Once inside the container, open the VSCode terminal and run:

```bash
java --version
javac --version
```

You should see Java 21 for both. You are ready for the course.

---

## Course Structure

```
java21-advance/
├── module-01-generics/
├── module-02-streams/
├── module-03-nio/
├── module-04-concurrency/
└── module-05-virtual-threads/
```

Each module contains a self-contained lab. Instructions will be given by the trainer during the session.

---

## Troubleshooting

**Docker Desktop not starting on Windows**

- Open Task Manager and confirm no Docker processes are stuck
- Restart Docker Desktop as Administrator

**"Reopen in Container" prompt not appearing**

- Confirm the Dev Containers extension is installed
- Hit `F1` and type `Dev Containers: Reopen in Container` manually

**Virtualisation error on Windows**

- Go back to Step 1 and confirm virtualisation is enabled in BIOS
- In Windows Features, ensure **Hyper-V** and **Windows Subsystem for Linux** are enabled:
  - Search → Turn Windows features on or off → tick both → restart
