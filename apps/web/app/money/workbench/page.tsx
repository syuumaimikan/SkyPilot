import type { Metadata } from "next";
import { MoneyWorkbench } from "@/components/money-workbench";
export const metadata: Metadata = { title: "Money Workbench" };
export default function Page() { return <MoneyWorkbench />; }
