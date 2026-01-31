# 🛍️ Online Cloth Store (Java Multi-threaded System)

A robust, full-stack Java application built on a **Client-Server architecture**. This project demonstrates real-time communication using Socket Programming and efficient handling of multiple users through Multithreading.

---

## 📝 Project Overview
This system simulates a real-world online shopping experience where multiple clients can connect to a central server simultaneously. It features a dynamic Graphical User Interface (GUI) for browsing products, managing a shopping cart, and simulating a secure payment checkout process.

## 🚀 Key Features
* **Multi-threaded Server:** Utilizes `ExecutorService` (Thread Pools) to handle multiple client connections concurrently without performance lag.
* **Dynamic GUI:** Developed using Java Swing and a custom `WrapLayout` to ensure the product display is responsive to window resizing.
* **Inventory Synchronization:** Uses `ReentrantLock` to prevent **Race Conditions**, ensuring that stock levels remain accurate even when multiple users buy at the same time.
* **Data Persistence:** All product information and stock levels are stored in and updated to a local `products.txt` file.
* **Asynchronous Payment:** Implements a simulated payment gateway that processes orders in the background, ensuring stock is only permanently deducted upon a successful transaction.

## 🛠️ Technical Stack
* **Language:** Java
* **Networking:** Java Sockets (TCP/IP)
* **GUI Library:** Java Swing & AWT
* **Concurrency:** Reentrant Locks, Atomic Integers, and Thread Pools

---

## 📸 Project Screenshots (Output)

| 🛍️ Main Shop View | 🛒 Shopping Cart |
| :---: | :---: |
| ![Shop View](./Screenshots/Store%20interface%20and%20Catalog.jfif) | ![Cart Management](./Screenshots/Shopping%20Cart%20Management.jfif) |

| 💳 Payment Processing | 🖥️ Server-Client Terminal |
| :---: | :---: |
| ![Payment Success](./Screenshots/Payment%20processing.jfif) | ![Terminal Logs](./Screenshots/Server%20Client%20Terminal%20Communication.png) |

---

## 📂 Project Structure
* `Client/`: Contains the `ClientGUI.java` and product `images/`.
* `Server/`: Contains `Server.java` and the request handling logic.
* `Data/`: Contains `products.txt` which manages the store inventory.

## ⚙️ How to Run
1.  **Start the Server:** Run `Server.java` first to initialize the product database and start listening for connections on port 5000.
2.  **Launch the Client:** Run `ClientGUI.java`. You can launch multiple instances of the client to test the multi-user functionality.
3.  **Shop:** Browse the catalog, add items to your cart, and proceed to checkout to see the payment simulation in action.

---

## 📄 License
This project is open-source and available under the **MIT License**.
