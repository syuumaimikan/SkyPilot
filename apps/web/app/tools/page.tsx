import type { Metadata } from "next";
import { PlayLab } from "@/components/play-lab";
export const metadata: Metadata = { title: "Play Lab · プレイラボ" };
export default function Page() { return <PlayLab/>; }
