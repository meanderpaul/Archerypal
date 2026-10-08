import { Hono } from "hono";
import type { AppVariables } from "../middleware/deviceAuth";

export const healthRoutes = new Hono<{ Bindings: Env; Variables: AppVariables }>();

healthRoutes.get("/health", (c) => c.json({ status: "ok" }));
