import AdminUserTable from "./AdminUserTable.jsx";

/**
 * Admin page (PRD Stories 8–11): review all users and manage their access —
 * enable/disable, change role, delete. Reachable only via the Admin nav item,
 * which is shown only to ADMIN accounts; the underlying `/api/admin/**` API is
 * enforced server-side regardless of what the UI exposes.
 */
export default function AdminPage() {
  return (
    <section aria-label="admin" className="flex flex-col gap-4">
      <div className="flex flex-col gap-1">
        <h2 className="text-xl font-semibold tracking-tight">User management</h2>
        <p className="text-sm text-muted-foreground">
          Review every registered account and manage access. You cannot disable,
          demote, or delete your own account.
        </p>
      </div>
      <AdminUserTable />
    </section>
  );
}
