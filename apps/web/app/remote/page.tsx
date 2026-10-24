import type { Metadata } from "next";
import { RustDeskPanel } from "@/components/rustdesk-panel";
export const metadata: Metadata = { title: "RustDesk · Remote PC" };
export default function Page() { return <RustDeskPanel />; }
