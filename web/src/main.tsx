import { render } from "preact";
import { App } from "./ui/App.tsx";
import "./ui/theme.css";

render(<App />, document.getElementById("app")!);

// Offline support. Registration failing is not worth surfacing: the app works
// either way, it just will not open without a connection.
if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => {
    void navigator.serviceWorker.register(`${import.meta.env.BASE_URL}sw.js`).catch(() => {});
  });
}
