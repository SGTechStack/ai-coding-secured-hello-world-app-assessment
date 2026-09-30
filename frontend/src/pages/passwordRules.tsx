/** Shown wherever a password is set; the API's Password policy enforces the same rules. */
const PASSWORD_RULES = [
  'At least 12 characters',
  'At most 72 bytes (fewer characters if you use accented letters or symbols)',
  'Not a password known from data breaches',
];

export function PasswordRules({ id }: { id: string }) {
  return (
    <ul id={id}>
      {PASSWORD_RULES.map((rule) => (
        <li key={rule}>{rule}</li>
      ))}
    </ul>
  );
}
