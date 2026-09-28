import { Link } from "react-router-dom";
import { Card, CenteredPage } from "../components/ui/Card";

export function NotFoundPage() {
  return (
    <CenteredPage>
      <Card title="Page not found">
        <p className="text-sm text-ink-muted">That address does not match anything in this app.</p>
        <p className="mt-4 text-sm">
          <Link to="/" className="text-accent underline hover:text-accent-hover">
            Go to the home page
          </Link>
        </p>
      </Card>
    </CenteredPage>
  );
}
