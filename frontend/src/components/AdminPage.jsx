import AdminUserTable from "./AdminUserTable.jsx";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardDescription,
} from "@/components/ui/card";

/**
 * Admin page (PRD Stories 8–11): review all users and manage their access —
 * enable/disable, change role, delete. Reachable only via the Admin nav item,
 * which is shown only to ADMIN accounts; the underlying `/api/admin/**` API is
 * enforced server-side regardless of what the UI exposes.
 */
export default function AdminPage() {
  return (
    <section aria-label="admin">
      <Card size="sm">
        <CardHeader>
          <CardTitle>User management</CardTitle>
          <CardDescription>
            Review every registered account and manage access. You cannot disable,
            demote, or delete your own account.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <AdminUserTable />
        </CardContent>
      </Card>
    </section>
  );
}
