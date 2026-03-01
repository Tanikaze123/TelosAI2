import { io } from "socket.io-client";

// This points to your 'server-backend' port
const URL = "http://localhost:4000";

export const socket = io(URL, {
  autoConnect: true, // Connects as soon as the app starts
});