import { createFileRoute } from '@tanstack/react-router';
import { RegisterPage } from '../pages/registration/RegisterPage';

export const Route = createFileRoute('/register')({ component: RegisterPage });
