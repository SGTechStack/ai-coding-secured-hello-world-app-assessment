import { useQuery } from "@tanstack/react-query";
import { CircleCheck } from "lucide-react";
import { meQueryOptions } from "../api/auth";
import { Card, CardDescription, CardHeader } from "@/components/ui/card";

export function LandingPage() {
  const { data: me } = useQuery(meQueryOptions);
  if (!me) return null;

  return (
    <Card className="w-full max-w-md">
      <CardHeader className="justify-items-center gap-3 text-center">
        <span className="flex size-12 items-center justify-center rounded-full bg-muted">
          <CircleCheck className="size-6" />
        </span>
        <h1 className="text-2xl font-semibold tracking-tight">
          Hello, {me.firstName}!
        </h1>
        <CardDescription>You're signed in.</CardDescription>
      </CardHeader>
    </Card>
  );
}
