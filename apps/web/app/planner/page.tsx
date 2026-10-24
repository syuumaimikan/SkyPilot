import type { Metadata } from "next";
import { AdventurePlanner } from "@/components/adventure";
export const metadata: Metadata = { title: "Adventure Notebook" };
export default function Page() { return <AdventurePlanner/>; }
