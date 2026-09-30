import { createFileRoute } from '@tanstack/react-router';
import { HomePage } from '../pages/home/HomePage';

export const Route = createFileRoute('/_authenticated/home')({ component: HomePage });
