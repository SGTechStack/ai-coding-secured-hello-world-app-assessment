const ROLE_LABELS = {
  USER: 'User',
  USER_MANAGER: 'User Manager',
};

fetch('/api/login-options')
  .then(res => res.json())
  .then(users => {
    const list = document.getElementById('user-list');
    list.innerHTML = '';
    users.forEach(({ username, roles }) => {
      const badges = (roles || []).map(role => {
        const cls = ROLE_LABELS[role] ? `role-${role}` : 'role-unknown';
        const label = ROLE_LABELS[role] ?? role;
        return `<span class="role-badge ${cls}">${label}</span>`;
      }).join('');
      const btn = document.createElement('button');
      btn.className = 'user-btn';
      btn.innerHTML = `
        <div class="avatar">${username[0]}</div>
        <div class="user-info">
          <span class="user-name">${username}</span>
          <span class="user-roles">${badges || '<span class="role-badge role-unknown">No roles</span>'}</span>
        </div>
      `;
      btn.addEventListener('click', () => {
        document.getElementById('username-input').value = username;
        document.getElementById('login-form').submit();
      });
      list.appendChild(btn);
    });
  })
  .catch(() => {
    document.getElementById('user-list').innerHTML =
      '<p class="error">Failed to load accounts. Is the backend running?</p>';
  });
