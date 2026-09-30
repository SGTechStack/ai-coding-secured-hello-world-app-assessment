import { Link } from 'react-router-dom';

export default function Hello({ user }) {
  return (
    <div className="ring">
      <h2>🎟️ Center Ring</h2>
      {user ? (
        <p className="greeting">Hello, {user.username}! 🎉</p>
      ) : (
        <p>
          You're not logged in yet. <Link className="inline-link" to="/login">Step right up</Link> to
          see your personalized greeting under the big top.
        </p>
      )}
    </div>
  );
}
