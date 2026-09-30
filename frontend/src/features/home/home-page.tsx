import { useGreeting } from '@features/greeting/greeting.queries';

export function HomePage() {
  const { data: greeting } = useGreeting();

  return (
    <div className="flex flex-1 items-center justify-center p-6">
      <h1 className="text-2xl font-semibold tracking-tight">{greeting}</h1>
    </div>
  );
}
