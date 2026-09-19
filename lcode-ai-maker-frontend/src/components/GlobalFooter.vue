<template>
  <a-layout-footer class="footer">
    <div class="footer-content">
      © 2026 resume-lcode ·
      <a href="https://mine-resume.lcode.space" target="_blank" rel="noopener noreferrer">
        Lcode
      </a>
    </div>
  </a-layout-footer>
</template>

<script setup lang="ts"></script>

<style scoped>
.footer {
  background: var(--c-surface-glass);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-top: 1px solid var(--c-border);
  text-align: center;
  padding: 22px 0;
  width: 100%;
  margin-top: auto;
}

.footer-content {
  color: var(--c-text-tertiary);
  font-size: 14px;
  letter-spacing: 0.01em;
}

.footer-content a {
  color: var(--c-primary);
  font-weight: 500;
  text-decoration: none;
  transition: color var(--t-base) var(--ease);
}

.footer-content a:hover {
  color: var(--c-accent-deep);
  text-decoration: underline;
}

@media (max-width: 768px) {
  .footer {
    padding: 16px 0;
  }

  .footer-content {
    font-size: 12px;
  }
}
</style>
